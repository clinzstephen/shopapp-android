package com.shopify.ui.checkout

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.domain.entity.Address
import com.domain.entity.Card
import com.domain.entity.CartProduct
import com.domain.entity.Customer
import com.domain.router.AppRouter
import com.shopify.ShopifyWrapper
import com.shopify.api.R
import com.shopify.constant.Extra
import com.shopify.entity.Checkout
import com.shopify.entity.ShippingRate
import com.shopify.ui.address.CheckoutAddressListActivity
import com.shopify.ui.address.CheckoutUnAuthAddressActivity
import com.shopify.ui.checkout.contract.CheckoutPresenter
import com.shopify.ui.checkout.contract.CheckoutView
import com.shopify.ui.checkout.di.CheckoutModule
import com.shopify.ui.checkout.view.ShippingOptionsView
import com.shopify.ui.payment.PaymentActivity
import com.shopify.ui.payment.card.CardActivity
import com.ui.base.lce.BaseActivity
import com.ui.base.lce.view.LceLayout
import com.ui.const.RequestCode
import kotlinx.android.synthetic.main.activity_checkout.*
import java.math.BigDecimal
import javax.inject.Inject

class CheckoutActivity :
    BaseActivity<Checkout, CheckoutView, CheckoutPresenter>(),
    CheckoutView,
    ShippingOptionsView.OnOptionSelectedListener {

    companion object {
        fun getStartIntent(context: Context) = Intent(context, CheckoutActivity::class.java)
    }

    @Inject
    lateinit var checkoutPresenter: CheckoutPresenter
    @Inject
    lateinit var router: AppRouter
    private var checkout: Checkout? = null
    private var customer: Customer? = null

    //ANDROID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(getString(R.string.checkout))

        setupListeners()
        loadData()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val isAddShippingAddress = requestCode == RequestCode.ADD_SHIPPING_ADDRESS
        val isEditShippingAddress = requestCode == RequestCode.EDIT_SHIPPING_ADDRESS
        val isAddBillingAddress = requestCode == RequestCode.ADD_BILLING_ADDRESS
        val isEditBillingAddress = requestCode == RequestCode.EDIT_BILLING_ADDRESS
        val isPayment = requestCode == RequestCode.PAYMENT
        val isCard = requestCode == RequestCode.CARD
        val isOkResult = resultCode == Activity.RESULT_OK
        if ((isAddShippingAddress || isEditShippingAddress) && isOkResult) {
            loadData()
        } else if (isPayment && isOkResult) {
            val paymentType = data?.getStringExtra(Extra.PAYMENT_TYPE)
            paymentView.setPaymentType(paymentType)
        } else if (isCard && isOkResult) {
            val card: Card? = data?.getParcelableExtra(Extra.CARD)
            val cardToken: String? = data?.getStringExtra(Extra.CARD_TOKEN)
            paymentView.setCardData(card, cardToken)
        } else if ((isAddBillingAddress || isEditBillingAddress) && isOkResult) {
            val address: Address? = data?.getParcelableExtra(Extra.ADDRESS)
            paymentView.setAddressData(address)
        }
    }

    //INIT

    override fun inject() {
        ShopifyWrapper.component
            .attachCheckoutComponent(CheckoutModule())
            .inject(this)
    }

    override fun getContentView() = R.layout.activity_checkout

    override fun createPresenter() = checkoutPresenter

    override fun useModalStyle() = true

    //SETUP

    private fun setupListeners() {
        shippingAddressView.setClickListeners(
            editClickListener = View.OnClickListener {
                checkout?.let {
                    val address = it.address
                    if (customer != null) {
                        startActivityForResult(
                            CheckoutAddressListActivity.getStartIntent(
                                this,
                                it.checkoutId,
                                true,
                                address
                            ),
                            RequestCode.EDIT_SHIPPING_ADDRESS
                        )
                    } else if (address != null) {
                        checkout?.let {
                            startActivityForResult(
                                CheckoutUnAuthAddressActivity.getStartIntent(this, it.checkoutId, address, true),
                                RequestCode.EDIT_SHIPPING_ADDRESS
                            )
                        }
                    }
                }
            },
            addAddressClickListener = View.OnClickListener {
                checkout?.let {
                    startActivityForResult(
                        CheckoutUnAuthAddressActivity.getStartIntent(this, it.checkoutId, isShipping = true),
                        RequestCode.ADD_SHIPPING_ADDRESS
                    )
                }
            }
        )
        paymentView.setClickListeners(
            paymentClickListener = View.OnClickListener {
                startActivityForResult(PaymentActivity.getStartIntent(this, paymentView.getPaymentType()), RequestCode.PAYMENT)
            },
            cardClickListener = View.OnClickListener {
                startActivityForResult(CardActivity.getStartIntent(this, paymentView.getCardData().first), RequestCode.CARD)
            },
            addAddressClickListener = View.OnClickListener {
                if (customer != null) {
                    startActivityForResult(CheckoutAddressListActivity.getStartIntent(
                        context = this,
                        isShipping = false,
                        selectedAddress = paymentView.getAddress()
                    ),
                        RequestCode.EDIT_BILLING_ADDRESS
                    )
                } else {
                    startActivityForResult(CheckoutUnAuthAddressActivity.getStartIntent(
                        context = this,
                        address = paymentView.getAddress(),
                        isShipping = false
                    ),
                        RequestCode.ADD_BILLING_ADDRESS
                    )
                }
            }
        )
        shippingOptionsView.onOptionSelectedListener = this
    }

    //LCE

    override fun loadData(pullToRefresh: Boolean) {
        super.loadData(pullToRefresh)
        val checkout = this.checkout
        if (checkout != null) {
            presenter.getCheckout(checkout.checkoutId)
        } else {
            presenter.getCartProductList()
        }
    }

    override fun showContent(data: Checkout) {
        super.showContent(data)
        checkout = data
        shippingAddressView.setAddress(data.address)
        priceView.setData(
            data.subtotalPrice, BigDecimal.ZERO,
            data.shippingRate?.price ?: BigDecimal.ZERO, data.totalPrice, data.currency
        )
        if (data.address != null) {
            presenter.getShippingRates(data.checkoutId)
        } else {
            shippingOptionsView.unSelectAddress()
        }
    }

    override fun customerReceived(customer: Customer?) {
        this.customer = customer
    }

    override fun cartProductListReceived(cartProductList: List<CartProduct>) {
        myCartView.setData(cartProductList)
    }

    override fun shippingRatesReceived(shippingRates: List<ShippingRate>) {
        checkout?.let {
            shippingOptionsView.setData(it, shippingRates)
        }
    }

    //CALLBACK

    override fun onOptionSelected(shippingRate: ShippingRate) {
        shippingOptionsView.selectShippingRate(shippingRate)
        changeState(LceLayout.LceState.LoadingState)
        checkout?.let { presenter.setShippingRates(it, shippingRate) }
    }
}